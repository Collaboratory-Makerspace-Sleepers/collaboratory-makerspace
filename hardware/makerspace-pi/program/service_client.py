"""Local GUI controls. Never opens the USB reader or GPIO."""
import json
import time
import uuid
from state import CONTROL,STATUS,read,write_private
class ServiceWorker:
    def __init__(self):self.desired=(0,'paused',{},{});self.sequence=0
    def configure(self,epoch,mode,cfg,key):
        self.desired=(epoch,mode,cfg,key)
        write_private(CONTROL,json.dumps({'id':str(uuid.uuid4()),'mode':mode,'at':time.time()}))
    def snapshot(self):return self.desired
    def start(self):pass
class NoHardware:
    def configure(self,*_):pass
    def close(self):pass

def messages_since(current, previous_pid, sequence):
    """A restarted service has a fresh sequence; never suppress its new scans."""
    pid=current.get('pid')
    if pid!=previous_pid:sequence=0
    messages={m['sequence']:m for m in current.get('recent',[])}
    if current.get('lastMessage'):
        messages[current['lastMessage']['sequence']]=current['lastMessage']
    fresh=[m for seq,m in sorted(messages.items()) if seq>sequence]
    return pid,max([sequence]+[m['sequence'] for m in fresh]),fresh

def status():
    data=read(STATUS)
    if not data or time.time()-data.get('updatedAt',0)>45:
        raise RuntimeError('Scanner service is not responding. Run: sudo systemctl restart collaboratory-scanner')
    return data
