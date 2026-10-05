"""Base：所有表映射类的父类。SQLAlchemy 要求表映射类都继承它，以便统一收集各个表的映射信息"""
# 第三方库
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """所有表映射类的父类"""
