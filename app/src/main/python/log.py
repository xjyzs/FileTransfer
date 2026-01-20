import sys
import threading

class RealtimeWriter:
    def __init__(self):
        self.buf = []
        self.lock = threading.Lock()

    def write(self, s):
        if not s:
            return

        if isinstance(s, bytes):
            try:
                s = s.decode("utf-8", errors="replace")
            except Exception:
                s = repr(s)

        with self.lock:
            self.buf.append(s)

    def flush(self):
        pass

    def poll(self):
        with self.lock:
            out = "".join(self.buf)
            self.buf.clear()
            return out


_writer = RealtimeWriter()

def start():
    sys.stdout = _writer
    sys.stderr = _writer

def poll():
    return _writer.poll()
