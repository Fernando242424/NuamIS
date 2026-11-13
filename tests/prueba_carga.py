#!/usr/bin/env python3
"""HU-23 / HU-25: prueba de carga documentada. 50 usuarios simultáneos consultan el mantenedor (filtros, búsqueda, paginación).
Uso: python3 tests/prueba_carga.py [URL] [correo] [usuarios] [consultas_por_usuario]
Requiere el servidor en marcha y datos de prueba (tests/datos_carga.sql). Resultado: percentiles y veredicto (<3 s en el 95 %)."""
import sys, json, ssl, time, threading, urllib.request, urllib.parse, http.cookiejar, statistics
URL = sys.argv[1] if len(sys.argv) > 1 else 'https://localhost:8443'
MAIL = sys.argv[2] if len(sys.argv) > 2 else 'corredor1@andes.cl'
USERS = int(sys.argv[3]) if len(sys.argv) > 3 else 50
PER = int(sys.argv[4]) if len(sys.argv) > 4 else 10
CTX = ssl._create_unverified_context()

def cliente():
    cj = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj), urllib.request.HTTPSHandler(context=CTX))
    def r(m, u, b=None):
        h = {'Content-Type': 'application/json'} if b is not None else {}
        if r.csrf and m != 'GET': h['X-CSRF'] = r.csrf
        rq = urllib.request.Request(URL + u, data=json.dumps(b).encode() if b is not None else None, headers=h, method=m)
        with op.open(rq) as x: return json.loads(x.read())
    r.csrf = None
    d = r('POST', '/auth/login', {'correo': MAIL, 'password': 'Nuam2026!'})
    me = r('POST', '/auth/2fa', {'codigo': d['demoCodigo']}) if d['siguiente'] != 'setup2fa' else None
    if me is None:
        i = r('GET', '/auth/2fa/setup'); me = r('POST', '/auth/2fa/setup', {'codigo': i['demoCodigo']})
    r.csrf = me['csrf']
    return r

CONSULTAS = ['/api/v1/calificaciones?pagina=1', '/api/v1/calificaciones?pagina=7', '/api/v1/calificaciones?mercado=CL&periodo=2026',
             '/api/v1/calificaciones?origen=corredor', '/api/v1/calificaciones?q=' + urllib.parse.quote('prueba-12'),
             '/api/v1/calificaciones?q=' + urllib.parse.quote('dividendo 99'), '/api/v1/calificaciones?mercado=PE&origen=bolsa&periodo=2025',
             '/api/v1/dashboard', '/api/v1/calificaciones?tipo_dj=1948&pagina=3', '/api/v1/calificaciones?periodo=2024&pagina=2']
tiempos, errores, lock = [], [], threading.Lock()

def usuario(k):
    try: r = cliente()
    except Exception as e:
        with lock: errores.append('login: ' + str(e)); return
    for i in range(PER):
        u = CONSULTAS[(k + i) % len(CONSULTAS)]
        t = time.time()
        try: r('GET', u); ok = True
        except Exception as e: ok = False; 
        dt = time.time() - t
        with lock:
            tiempos.append(dt)
            if not ok: errores.append(u)

t0 = time.time()
hs = [threading.Thread(target=usuario, args=(k,)) for k in range(USERS)]
[h.start() for h in hs]; [h.join() for h in hs]
tiempos.sort(); n = len(tiempos)
p = lambda q: tiempos[min(n - 1, int(n * q))]
print(f'Usuarios simultáneos: {USERS} · consultas: {n} · errores: {len(errores)} · duración total: {time.time()-t0:.1f}s')
print(f'media {statistics.mean(tiempos):.3f}s · p50 {p(.5):.3f}s · p95 {p(.95):.3f}s · p99 {p(.99):.3f}s · máx {tiempos[-1]:.3f}s')
ok = p(.95) < 3 and not errores
print('RESULTADO:', 'CUMPLE (95 % < 3 s)' if ok else 'NO CUMPLE'); sys.exit(0 if ok else 1)
