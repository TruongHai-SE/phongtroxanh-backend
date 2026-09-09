#!/bin/bash
set -e

if [ "$1" = 'postgres' ]; then
    mkdir -p "$PGDATA" /run/postgresql
    chown -R postgres:postgres "$PGDATA" /run/postgresql

    if [ ! -s "$PGDATA/PG_VERSION" ]; then
        su-exec postgres initdb -D "$PGDATA" --auth-local=trust --auth-host=md5
        
        echo "host all all all md5" >> "$PGDATA/pg_hba.conf"
        echo "listen_addresses='*'" >> "$PGDATA/postgresql.conf"

        su-exec postgres pg_ctl -D "$PGDATA" -w start

        # Create user and db
        su-exec postgres psql -v ON_ERROR_STOP=1 --username postgres <<-EOSQL
            CREATE USER $POSTGRES_USER WITH SUPERUSER PASSWORD '$POSTGRES_PASSWORD';
            CREATE DATABASE $POSTGRES_DB OWNER $POSTGRES_USER;
EOSQL

        # Execute init scripts
        for f in /docker-entrypoint-initdb.d/*; do
            case "$f" in
                *.sql)    echo "$0: running $f"; su-exec postgres psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"; echo ;;
                *.sql.gz) echo "$0: running $f"; gunzip -c "$f" | su-exec postgres psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"; echo ;;
                *)        echo "$0: ignoring $f" ;;
            esac
        done

        su-exec postgres pg_ctl -D "$PGDATA" -m fast -w stop
    fi

    exec su-exec postgres postgres -D "$PGDATA"
fi

exec "$@"
