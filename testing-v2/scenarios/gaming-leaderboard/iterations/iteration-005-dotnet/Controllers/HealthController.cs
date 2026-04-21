using Microsoft.AspNetCore.Mvc;

namespace GamingLeaderboard.Controllers;

[ApiController]
public class HealthController : ControllerBase
{
    [HttpGet("/health")]
    public IActionResult Health()
    {
        return Ok(new { status = "healthy" });
    }
}
