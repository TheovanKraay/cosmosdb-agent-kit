using Microsoft.Azure.Cosmos;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly CosmosClient _client;
    private readonly string _databaseName;
    private Database? _database;
    private Container? _playersContainer;
    private Container? _scoresContainer;
    private Container? _leaderboardContainer;
    private readonly SemaphoreSlim _initLock = new(1, 1);
    private bool _initialized;

    public Container PlayersContainer => _playersContainer
        ?? throw new InvalidOperationException("CosmosDbService not initialized");
    public Container ScoresContainer => _scoresContainer
        ?? throw new InvalidOperationException("CosmosDbService not initialized");
    public Container LeaderboardContainer => _leaderboardContainer
        ?? throw new InvalidOperationException("CosmosDbService not initialized");
    public bool IsInitialized => _initialized;

    public CosmosDbService(CosmosClient client, string databaseName)
    {
        _client = client;
        _databaseName = databaseName;
    }

    public async Task EnsureInitializedAsync()
    {
        if (_initialized) return;
        await _initLock.WaitAsync();
        try
        {
            if (_initialized) return;
            await InitializeAsync();
            _initialized = true;
        }
        finally
        {
            _initLock.Release();
        }
    }

    private async Task InitializeAsync()
    {
        // Create database with autoscale throughput
        var databaseResponse = await _client.CreateDatabaseIfNotExistsAsync(_databaseName);
        _database = databaseResponse.Database;
        try
        {
            await _database.ReplaceThroughputAsync(
                ThroughputProperties.CreateAutoscaleThroughput(1000));
        }
        catch
        {
            // Throughput may already be configured or not supported (emulator)
        }

        // Players container - partitioned by /playerId for efficient point reads
        var playersProperties = new ContainerProperties("players", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/displayName/?" },
                    new ExcludedPath { Path = "/totalScore/?" },
                    new ExcludedPath { Path = "/schemaVersion/?" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                }
            }
        };
        _playersContainer = (await _database.CreateContainerIfNotExistsAsync(playersProperties)).Container;

        // Scores container - partitioned by /playerId for efficient player score history
        var scoresProperties = new ContainerProperties("scores", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/gameMode/?" },
                    new ExcludedPath { Path = "/schemaVersion/?" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                },
                CompositeIndexes =
                {
                    new System.Collections.ObjectModel.Collection<CompositePath>
                    {
                        new CompositePath { Path = "/playerId", Order = CompositePathSortOrder.Ascending },
                        new CompositePath { Path = "/timestamp", Order = CompositePathSortOrder.Descending }
                    }
                }
            }
        };
        _scoresContainer = (await _database.CreateContainerIfNotExistsAsync(scoresProperties)).Container;

        // Leaderboard container - partitioned by synthetic /leaderboardKey for efficient top-N queries
        var leaderboardProperties = new ContainerProperties("leaderboard", "/leaderboardKey")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/schemaVersion/?" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                },
                CompositeIndexes =
                {
                    new System.Collections.ObjectModel.Collection<CompositePath>
                    {
                        new CompositePath { Path = "/score", Order = CompositePathSortOrder.Descending },
                        new CompositePath { Path = "/displayName", Order = CompositePathSortOrder.Ascending }
                    }
                }
            }
        };
        _leaderboardContainer = (await _database.CreateContainerIfNotExistsAsync(leaderboardProperties)).Container;
    }
}
