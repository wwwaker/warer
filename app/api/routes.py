from fastapi import APIRouter

from app.models.schemas import ComputeRequest, ComputeResponse
from app.core.engine import SympyEngine

router = APIRouter()


@router.post("/v1/compute", response_model=ComputeResponse)
def compute(req: ComputeRequest) -> ComputeResponse:
    result, error, elapsed = SympyEngine.process_expression(req.payload.expression)

    if error:
        return ComputeResponse(
            status_code=400,
            execution_time=f"{elapsed:.1f}ms",
            result=None,
            error=error,
        )

    return ComputeResponse(
        status_code=200,
        execution_time=f"{elapsed:.1f}ms",
        result=result,
        error=None,
    )
