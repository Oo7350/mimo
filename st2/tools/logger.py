"""日志工具 —— 与 Selenium 版完全一致，无需变化"""
import logging
from logging import handlers
from config import LOG_DIR


class GetLog:
    """单例日志器"""
    __log = None

    @classmethod
    def get_log(cls):
        if cls.__log is None:
            cls.__log = logging.getLogger("playwright")
            cls.__log.setLevel(logging.INFO)
            filename = LOG_DIR + "/web.log"
            tf = logging.handlers.TimedRotatingFileHandler(
                filename=filename,
                when="midnight",
                interval=1,
                backupCount=3,
                encoding="utf-8"
            )
            fmt = "%(asctime)s %(levelname)s [%(filename)s(%(funcName)s:%(lineno)d)] - %(message)s"
            fm = logging.Formatter(fmt)
            tf.setFormatter(fm)
            cls.__log.addHandler(tf)
        return cls.__log
