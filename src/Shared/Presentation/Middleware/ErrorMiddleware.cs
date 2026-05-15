using Somnguard.Backend.Shared.Application.Dto;
using Somnguard.Backend.Shared.Domain.Exceptions;

namespace Somnguard.Backend.Shared.Presentation.Middleware;

public sealed class ErrorMiddleware
{
    private const string InternalErrorCode = "INTERNAL_ERROR";
    private const string InternalErrorMessage = "Error inesperado";

    private readonly RequestDelegate _next;
    private readonly ILogger<ErrorMiddleware> _logger;

    public ErrorMiddleware(RequestDelegate next, ILogger<ErrorMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (AppException ex)
        {
            if (context.Response.HasStarted)
            {
                throw;
            }

            context.Response.Clear();
            context.Response.StatusCode = ex.StatusCode;
            context.Response.ContentType = "application/json";

            var response = new ErrorResponseDto(
                ex.Code,
                ex.Message,
                DateTime.UtcNow
            );

            await context.Response.WriteAsJsonAsync(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unhandled exception while processing request {Method} {Path}", context.Request.Method, context.Request.Path);

            if (context.Response.HasStarted)
            {
                throw;
            }

            context.Response.Clear();
            context.Response.StatusCode = StatusCodes.Status500InternalServerError;
            context.Response.ContentType = "application/json";

            var response = new ErrorResponseDto(
                InternalErrorCode,
                InternalErrorMessage,
                DateTime.UtcNow
            );

            await context.Response.WriteAsJsonAsync(response);
        }
    }
}
