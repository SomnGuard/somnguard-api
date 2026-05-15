# somnguard-api

## Manejo global de errores

Este proyecto usa un middleware global de errores para centralizar el control de excepciones y responder siempre con un formato consistente.

La implementación vive en estas piezas:

- [src/Program.cs](src/Program.cs) registra el middleware en la pipeline.
- [src/shared/presentation/Middleware/ErrorMiddleware.cs](src/shared/presentation/Middleware/ErrorMiddleware.cs) captura y transforma las excepciones en respuestas HTTP.
- [src/shared/domain/Exceptions/AppException.cs](src/shared/domain/Exceptions/AppException.cs) representa errores de negocio conocidos.
- [src/shared/application/Dto/ErrorResponseDto.cs](src/shared/application/Dto/ErrorResponseDto.cs) define el contrato de respuesta de error.

## Como funciona

El middleware intercepta cualquier excepción que ocurra durante el procesamiento de una request.

### 1. Error de negocio controlado

Cuando una capa del sistema lanza `AppException`, el middleware responde con:

- el `status code` definido por la excepción
- un `code` funcional, por ejemplo `PRODUCT_NOT_FOUND`
- un mensaje claro para el negocio
- un `timestamp` en UTC

Ejemplo:

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "El producto no existe",
  "timestamp": "2026-05-06T12:34:56.789Z"
}
```

### 2. Error inesperado

Si ocurre cualquier otra excepción no controlada, el middleware responde con:

- status code `500`
- `code = INTERNAL_ERROR`
- mensaje genérico `Error inesperado`
- `timestamp` en UTC

Ejemplo:

```json
{
  "code": "INTERNAL_ERROR",
  "message": "Error inesperado",
  "timestamp": "2026-05-06T12:34:56.789Z"
}
```

## Dónde se implementa

El middleware se registra en [src/Program.cs](src/Program.cs) con `app.UseMiddleware<ErrorMiddleware>()` antes de `MapControllers()`.

Ese orden es importante porque el middleware debe envolver a los endpoints y a los controladores para poder capturar cualquier excepción lanzada dentro de la request.

## Como usarlo

### Lanzar un error de negocio

Desde cualquier servicio, caso de uso o controlador puedes lanzar una `AppException`.

```csharp
using Somnguard.Backend.Shared.Domain.Exceptions;

if (product is null)
{
	throw AppException.NotFound("PRODUCT_NOT_FOUND", "El producto no existe");
}
```

### Crear tu propia excepción de negocio

Si necesitas una excepción con otro status code, crea una instancia directa:

```csharp
throw new AppException(
	"STOCK_NOT_AVAILABLE",
	"No hay stock suficiente",
	StatusCodes.Status409Conflict
);
```

## Ejemplo de controlador

```csharp
using Microsoft.AspNetCore.Mvc;
using Somnguard.Backend.Shared.Domain.Exceptions;

[ApiController]
[Route("api/products")]
public class ProductsController : ControllerBase
{
	[HttpGet("{id}")]
	public IActionResult GetById(Guid id)
	{
		var product = null;

		if (product is null)
		{
			throw AppException.NotFound("PRODUCT_NOT_FOUND", "El producto no existe");
		}

		return Ok(product);
	}
}
```

## Resumen de flujo

1. Entra una request al API.
2. `ErrorMiddleware` envuelve la ejecución.
3. Si el código lanza `AppException`, se devuelve el error de negocio esperado.
4. Si ocurre una excepción inesperada, se devuelve una respuesta genérica con `500`.
5. El cliente siempre recibe una estructura de error uniforme.

## Resultado esperado

Con este enfoque el API queda listo para crecer con reglas de negocio sin repetir `try/catch` en cada controlador o servicio. El control de errores queda centralizado, más fácil de mantener y más consistente para los consumidores del API.