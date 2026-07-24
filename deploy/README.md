# Deploy del backend

El porqué completo está en la [Lección 03 del handbook](https://github.com/davidpp09/restfood-handbook/blob/master/lecciones/03-deploy-y-rollback.md).

## Uso diario

```bash
./deploy/deploy.sh      # despliega el main actual (exige CI en verde)
./deploy/rollback.sh    # vuelve a la release anterior en segundos
```

## Layout en el servidor

```
~/deploys/restfood-backend/
├── current.jar  -> releases/<fecha>-<sha>.jar   (la que corre el service)
├── previous.jar -> releases/<fecha>-<sha>.jar   (a la que vuelve el rollback)
└── releases/                                    (últimas 5, versionadas por fecha+commit)
```

## Instalación (ya hecha; referencia por si se reinstala el servidor)

```bash
mkdir -p ~/deploys/restfood-backend/releases
sudo mkdir -p /etc/restfood
sudo tee /etc/restfood/backend.env >/dev/null <<'EOF'   # con los valores reales
JWT_SECRET=...
DB_PASSWORD=...
EOF
sudo chmod 600 /etc/restfood/backend.env
sudo cp deploy/restfood-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl restart restfood-backend
```
