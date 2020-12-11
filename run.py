import sys
import logging
import traceback

from train import train
from utils import create_dirs, fix_seeds, configure_logger

fix_seeds(1234)

if __name__ == '__main__':
    create_dirs()
    configure_logger(name='ggnn-mnp')
    try:
        train(100)
    except Exception as e:
        sys.stderr.write(traceback.format_exc())
        logging.exception(e)
