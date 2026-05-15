using Somnguard.Backend.Shared.Presentation.Middleware;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddControllers();

var app = builder.Build();

app.UseMiddleware<ErrorMiddleware>();

app.MapGet("/", () => Results.Ok(new
{
    service = "somnguard-backend",
    status = "running"
}));

app.MapGet("/health", () => Results.Ok(new
{
    status = "healthy"
}));

app.MapControllers();

app.Run();