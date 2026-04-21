using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Services;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);

var cosmosEndpoint = Environment.GetEnvironmentVariable("COSMOS_ENDPOINT") ?? "https://localhost:8081";
var cosmosKey = Environment.GetEnvironmentVariable("COSMOS_KEY")
    ?? "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";

var cosmosClientOptions = new CosmosClientOptions
{
    ConnectionMode = ConnectionMode.Gateway,
    HttpClientFactory = () =>
    {
        var handler = new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback =
                HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
        };
        return new HttpClient(handler);
    }
};

var cosmosClient = new CosmosClient(cosmosEndpoint, cosmosKey, cosmosClientOptions);
var cosmosService = new CosmosDbService(cosmosClient);

builder.Services.AddSingleton(cosmosClient);
builder.Services.AddSingleton(cosmosService);
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    });

var app = builder.Build();

await cosmosService.InitializeAsync();

app.MapControllers();

app.Run();
