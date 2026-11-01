from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    应用配置
    """
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    host: str = "127.0.0.1"
    port: int = 7077
    java_api_base_url: str
    llm_api_key: SecretStr
    internal_token: str
    tavily_api_key: str
    redis_url: str


@lru_cache
def get_settings() -> Settings:
    """
    获取应用配置
    :return: 应用配置
    """
    return Settings()
