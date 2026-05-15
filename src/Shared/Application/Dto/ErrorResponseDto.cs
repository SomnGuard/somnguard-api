namespace Somnguard.Backend.Shared.Application.Dto;

public sealed record ErrorResponseDto(
    string Code,
    string Message,
    DateTime Timestamp
);
