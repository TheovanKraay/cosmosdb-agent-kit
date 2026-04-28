using Microsoft.Azure.Cosmos;
using Microsoft.Azure.Cosmos.Fluent;
using Newtonsoft.Json;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly CosmosClient _client;
    private readonly string _databaseName;
    private Database? _database;
    private Container? _playersContainer;
    private Container? _scoresContainer;
    private Container? _leaderboardsContainer;
    private readonly ILogger<CosmosDbService> _logger;
    private readonly SemaphoreSlim _initLock = new(1, 1);
    private bool _initialized;

    public const string PlayersContainerName = "players";
    public const string ScoresContainerName = "scores";
    public const string LeaderboardsContainerName = "leaderboards";

    public CosmosDbService(ILogger<CosmosDbService> logger)
    {
        _logger = logger;
        var endpoint = Environment.GetEnvironmentVariable("COSMOS_ENDPOINT") ?? "https://localhost:8081";
        var key = Environment.GetEnvironmentVariable("COSMOS_KEY")
            ?? "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
        _databaseName = Environment.GetEnvironmentVariable("COSMOS_DATABASE") ?? "gaming-leaderboard";

        _client = new CosmosClientBuilder(endpoint, key)
            .WithConnectionModeGateway()
            .WithHttpClientFactory(() =>
            {
                var handler = new HttpClientHandler
                {
                    ServerCertificateCustomValidationCallback =
                        HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
                };
                return new HttpClient(handler);
            })
            .WithCustomSerializer(new NewtonsoftJsonCosmosSerializer())
            .Build();
    }

    public CosmosClient Client => _client;

    /// <summary>
    /// Initialize database and containers. Safe to call multiple times —
    /// uses a semaphore to ensure only one initialization runs.
    /// </summary>
    public async Task InitializeAsync()
    {
        if (_initialized) return;

        await _initLock.WaitAsync();
        try
        {
            if (_initialized) return;

            _logger.LogInformation("Initializing Cosmos DB...");

            var dbResponse = await _client.CreateDatabaseIfNotExistsAsync(
                _databaseName,
                ThroughputProperties.CreateAutoscaleThroughput(1000));
            _database = dbResponse.Database;

            // Players container: partitioned by /playerId
            var playersProps = new ContainerProperties(PlayersContainerName, "/playerId")
            {
                IndexingPolicy = new IndexingPolicy
                {
                    IncludedPaths = { new IncludedPath { Path = "/playerId/?" }, new IncludedPath { Path = "/region/?" } },
                    ExcludedPaths = { new ExcludedPath { Path = "/*" } }
                }
            };
            var playersResponse = await _database.CreateContainerIfNotExistsAsync(playersProps);
            _playersContainer = playersResponse.Container;

            // Scores container: partitioned by /playerId
            var scoresProps = new ContainerProperties(ScoresContainerName, "/playerId")
            {
                IndexingPolicy = new IndexingPolicy
                {
                    IncludedPaths =
                    {
                        new IncludedPath { Path = "/playerId/?" },
                        new IncludedPath { Path = "/timestamp/?" },
                        new IncludedPath { Path = "/score/?" }
                    },
                    ExcludedPaths = { new ExcludedPath { Path = "/*" } }
                }
            };
            scoresProps.IndexingPolicy.CompositeIndexes.Add(new System.Collections.ObjectModel.Collection<CompositePath>
            {
                new CompositePath { Path = "/timestamp", Order = CompositePathSortOrder.Descending },
                new CompositePath { Path = "/score", Order = CompositePathSortOrder.Descending }
            });
            var scoresResponse = await _database.CreateContainerIfNotExistsAsync(scoresProps);
            _scoresContainer = scoresResponse.Container;

            // Leaderboards container: partitioned by /leaderboardKey (synthetic key)
            var lbProps = new ContainerProperties(LeaderboardsContainerName, "/leaderboardKey")
            {
                IndexingPolicy = new IndexingPolicy
                {
                    IncludedPaths =
                    {
                        new IncludedPath { Path = "/leaderboardKey/?" },
                        new IncludedPath { Path = "/bestScore/?" },
                        new IncludedPath { Path = "/displayName/?" },
                        new IncludedPath { Path = "/playerId/?" }
                    },
                    ExcludedPaths = { new ExcludedPath { Path = "/*" } }
                }
            };
            lbProps.IndexingPolicy.CompositeIndexes.Add(new System.Collections.ObjectModel.Collection<CompositePath>
            {
                new CompositePath { Path = "/bestScore", Order = CompositePathSortOrder.Descending },
                new CompositePath { Path = "/displayName", Order = CompositePathSortOrder.Ascending }
            });
            var lbResponse = await _database.CreateContainerIfNotExistsAsync(lbProps);
            _leaderboardsContainer = lbResponse.Container;

            _initialized = true;
            _logger.LogInformation("Cosmos DB initialization complete.");
        }
        finally
        {
            _initLock.Release();
        }
    }

    /// <summary>
    /// Ensure initialization has completed. Call this before accessing containers.
    /// </summary>
    public async Task EnsureInitializedAsync()
    {
        if (!_initialized)
        {
            await InitializeAsync();
        }
    }

    public Container PlayersContainer =>
        _playersContainer ?? throw new InvalidOperationException("Cosmos DB not initialized");

    public Container ScoresContainer =>
        _scoresContainer ?? throw new InvalidOperationException("Cosmos DB not initialized");

    public Container LeaderboardsContainer =>
        _leaderboardsContainer ?? throw new InvalidOperationException("Cosmos DB not initialized");

    public Database Database =>
        _database ?? throw new InvalidOperationException("Cosmos DB not initialized");
}

public class NewtonsoftJsonCosmosSerializer : CosmosSerializer
{
    private static readonly JsonSerializerSettings _settings = new()
    {
        NullValueHandling = NullValueHandling.Ignore,
        DefaultValueHandling = DefaultValueHandling.Include
    };

    public override T FromStream<T>(Stream stream)
    {
        using var sr = new StreamReader(stream);
        using var jsonReader = new JsonTextReader(sr);
        var serializer = JsonSerializer.Create(_settings);
        return serializer.Deserialize<T>(jsonReader)!;
    }

    public override Stream ToStream<T>(T input)
    {
        var ms = new MemoryStream();
        using var sw = new StreamWriter(ms, leaveOpen: true);
        using var jsonWriter = new JsonTextWriter(sw);
        var serializer = JsonSerializer.Create(_settings);
        serializer.Serialize(jsonWriter, input);
        jsonWriter.Flush();
        sw.Flush();
        ms.Position = 0;
        return ms;
    }
}
