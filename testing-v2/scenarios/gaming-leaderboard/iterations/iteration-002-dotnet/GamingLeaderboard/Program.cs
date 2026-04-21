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

// Initialize Cosmos DB on startup
using (var scope = app.Services.CreateScope())
{
    var cosmos = scope.ServiceProvider.GetRequiredService<CosmosDbService>();
    await cosmos.InitializeAsync();
}

app.Run();
