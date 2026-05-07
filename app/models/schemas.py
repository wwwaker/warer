from typing import Optional
from pydantic import BaseModel, Field


class OutputConfig(BaseModel):
    format: str = Field(default="latex", pattern=r"^(latex|mathml|plain)$")
    precision: int = Field(default=15, ge=1, le=50)
    simplify: bool = True


class ComputePayload(BaseModel):
    expression: str
    engine_hint: str = Field(default="auto", pattern=r"^(auto|symbolic|numeric)$")
    output_config: OutputConfig = Field(default_factory=OutputConfig)


class ComputeRequest(BaseModel):
    payload: ComputePayload


class ComputeResult(BaseModel):
    is_symbolic: bool = True
    main_display: str = ""
    plain_text: str = ""
    numeric_approximation: Optional[str] = None
    variables: list[str] = Field(default_factory=list)


class ErrorDetail(BaseModel):
    type: str
    message: str
    position: Optional[int] = None


class ComputeResponse(BaseModel):
    status_code: int = 200
    execution_time: str = ""
    result: Optional[ComputeResult] = None
    error: Optional[ErrorDetail] = None
