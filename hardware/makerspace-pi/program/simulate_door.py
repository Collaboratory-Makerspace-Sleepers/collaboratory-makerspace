#!/usr/bin/env python3
"""Simulation only: no GPIO import, network requests, reader or actual unlock."""
from door import Door
class Output:
    def __init__(self,*args,**kwargs):self.value=False
    def on(self):self.value=True;print('SIMULATION: request LOCKED')
    def off(self):self.value=False;print('SIMULATION: request RELEASED')
    def close(self):pass
class Timer:
    def __init__(self,seconds,callback):self.seconds=seconds;self.callback=callback
    def start(self):print(f'SIMULATION: local timer would wait {self.seconds} seconds')
    def cancel(self):pass

def main():
    door=Door(factory=Output,timer_factory=Timer)
    door.configure({'door_gpio_enabled':True,'door_gpio_pin':17,'door_active_high':True},{'doorEnabled':True})
    deny={'granted':False,'error':False,'door':{'enabled':True,'unlockSeconds':5}}
    assert not door.approved(deny);print('SIMULATION: denied card did not pulse')
    assert door.approved({**deny,'granted':True});door.timer.callback()
    door.close();print('Simulation passed. No physical pin or door was used.')
if __name__=='__main__':main()
