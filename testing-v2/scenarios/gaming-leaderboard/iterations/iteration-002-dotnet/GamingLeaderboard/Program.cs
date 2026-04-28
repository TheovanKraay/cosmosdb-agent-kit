using GamingLeaderboard.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure JSON serialization for camelCase
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
    });

// Register Cosmos DB as singleton (best practice: reuse CosmosClient)
builder.Services.AddSingleton<CosmosDbService>();

var app = builder.Build();

app.MapControllers();

// Initialize Cosmos DB in background AFTER the server starts listening.
// This prevents blocking the health endpoint — the CI harness polls /health
// and will time out if the server isn't listening yet.
app.Lifetime.ApplicationStarted.Register(() =>
{
    var cosmos = app.Services.GetRequiredService<CosmosDbService>();
    _ = Task.Run(async () =>
    {
        try
        {
            await cosmos.InitializeAsync();
        }
        catch (Exception ex)
        {
            var logger = app.Services.GetRequiredService<ILogger<Program>>();
            logger.LogError(ex, "Failed to initialize Cosmos DB");
        }
    });
});

app.Run();
