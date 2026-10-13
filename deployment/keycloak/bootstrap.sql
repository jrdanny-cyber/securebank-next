\set ON_ERROR_STOP on
\getenv keycloak_password KC_DB_PASSWORD

BEGIN;

CREATE ROLE keycloak_app
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'keycloak_password';

GRANT keycloak_app TO banking_admin WITH INHERIT FALSE, SET TRUE;

COMMIT;

CREATE DATABASE keycloak OWNER keycloak_app;

SET ROLE keycloak_app;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
RESET ROLE;

\connect keycloak

SET ROLE keycloak_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
RESET ROLE;

REVOKE SET OPTION FOR keycloak_app FROM banking_admin;

SELECT datname, pg_get_userbyid(datdba) AS owner
FROM pg_database WHERE datname = 'keycloak';

SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication
FROM pg_roles WHERE rolname = 'keycloak_app';

SELECT ssl, version FROM pg_stat_ssl WHERE pid = pg_backend_pid();
