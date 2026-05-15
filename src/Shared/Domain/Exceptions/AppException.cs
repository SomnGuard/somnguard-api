namespace Somnguard.Backend.Shared.Domain.Exceptions;

public class AppException : Exception
{
    public AppException(string code, string message, int statusCode)
        : base(message)
    {
        Code = code;
        StatusCode = statusCode;
    }

    public string Code { get; }

    public int StatusCode { get; }

    public static AppException NotFound(string code, string message)
    {
        return new AppException(code, message, StatusCodes.Status404NotFound);
    }

    public static AppException BadRequest(string code, string message)
    {
        return new AppException(code, message, StatusCodes.Status400BadRequest);
    }

    public static AppException Unauthorized(string code, string message)
    {
        return new AppException(code, message, StatusCodes.Status401Unauthorized);
    }
}
