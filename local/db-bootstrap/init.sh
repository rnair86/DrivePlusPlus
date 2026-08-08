#!/bin/bash
set -e
set -u

function create_user_and_database() {
    local database=$1
    echo "  Creating user and database '$database'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
        CREATE USER $database WITH PASSWORD '${database}123';
        CREATE DATABASE $database OWNER $database;
        GRANT ALL PRIVILEGES ON DATABASE $database TO $database;
EOSQL

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" <<-EOSQL
        ALTER SCHEMA public OWNER TO $database;
        GRANT USAGE, CREATE ON SCHEMA public TO $database;
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "Multiple databases requested: $POSTGRES_MULTIPLE_DATABASES"
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_user_and_database $db
    done
    echo "Multiple databases created"
fi



##!/bin/bash
#set -e
## Creates databases listed in POSTGRES_MULTIPLE_DATABASES (comma-separated)
#if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
#    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
#        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -c "CREATE DATABASE \"$db\";"
#    done
#fi
