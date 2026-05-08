# Docker Image Pull Script
# Usage: powershell -ExecutionPolicy Bypass -File pull-images.ps1

$images = @(
    "redis:7.2-alpine",
    "quay.io/coreos/etcd:v3.5.5",
    "minio/minio:RELEASE.2023-03-20T20-16-18Z",
    "mysql:8.0",
    "milvusdb/milvus:v2.4.11",
    "apache/rocketmq:5.3.2",
    "docker.elastic.co/elasticsearch/elasticsearch:8.12.0"
)

$successCount = 0
$failCount = 0
$failedImages = @()

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Docker Image Pull Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

foreach ($image in $images) {
    Write-Host "[$($images.IndexOf($image) + 1)/$($images.Count)] Pulling $image" -ForegroundColor Yellow
    Write-Host "----------------------------------------" -ForegroundColor Gray
    
    $retry = 0
    $maxRetries = 3
    $success = $false
    
    while ($retry -lt $maxRetries -and -not $success) {
        if ($retry -gt 0) {
            Write-Host "  Retry attempt $($retry + 1)/$maxRetries..." -ForegroundColor Yellow
            Start-Sleep -Seconds 3
        }
        
        docker pull $image 2>&1 | ForEach-Object {
            if ($_ -match "Pull complete|Downloaded newer image") {
                Write-Host "  $_" -ForegroundColor Green
            } elseif ($_ -match "error|Error|failed") {
                Write-Host "  $_" -ForegroundColor Red
            } else {
                Write-Host "  $_" -ForegroundColor Gray
            }
        }
        
        if ($LASTEXITCODE -eq 0) {
            $success = $true
            $successCount++
            Write-Host "  ✓ Successfully pulled $image" -ForegroundColor Green
        } else {
            $retry++
        }
    }
    
    if (-not $success) {
        $failCount++
        $failedImages += $image
        Write-Host "  ✗ Failed to pull $image after $maxRetries attempts" -ForegroundColor Red
    }
    
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Pull Completed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Success: $successCount / $($images.Count)" -ForegroundColor Green

if ($failCount -gt 0) {
    Write-Host "Failed: $failCount / $($images.Count)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Failed images:" -ForegroundColor Red
    foreach ($failedImage in $failedImages) {
        Write-Host "  - $failedImage" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Suggestions:" -ForegroundColor Yellow
    Write-Host "  1. Check network connection" -ForegroundColor Yellow
    Write-Host "  2. Configure Docker registry mirror" -ForegroundColor Yellow
    Write-Host "  3. Use domestic mirror source manually" -ForegroundColor Yellow
    Write-Host "  4. Reference: docker/Image-Pull-Guide.md" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "All images pulled successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  cd d:\JavaProject\agent_service\docker" -ForegroundColor White
    Write-Host "  docker-compose up -d" -ForegroundColor White
}

Write-Host ""
Write-Host "Image list:" -ForegroundColor Cyan
docker images | Select-String "mysql|redis|elasticsearch|etcd|minio|milvus|rocketmq|REPOSITORY"
