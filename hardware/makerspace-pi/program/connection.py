"""Connection choices independent of the desktop UI."""
from api import origin

def hosted_config(current, address):
    address=origin(address)
    if not address.startswith('https://'):
        raise ValueError('Hosted backends need an HTTPS address, for example https://makerspace.example.edu')
    return {**current,'origin':address,'connection_mode':'hosted','server_id':'','tls_fingerprint':'','laptop_name':''}

def credentials_match(config, credentials):
    if not credentials:return False
    if config.get('connection_mode')=='hosted':
        return bool(credentials.get('backendOrigin')) and credentials['backendOrigin']==config['origin'] and not credentials.get('serverId') and not credentials.get('fingerprint')
    if credentials.get('serverId'):
        return credentials['serverId']==config.get('server_id') and credentials.get('fingerprint')==config.get('tls_fingerprint')
    # Backward-compatible loopback tunnel setup.
    return not credentials.get('backendOrigin') or credentials['backendOrigin']==config['origin']
