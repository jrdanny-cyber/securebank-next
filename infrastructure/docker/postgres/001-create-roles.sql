\set ON_ERROR_STOP on

\getenv migrator_password BANKING_MIGRATOR_PASSWORD
\getenv app_password BANKING_APP_PASSWORD

BEGIN;

CREATE ROLE banking_migrator
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'migrator_password';

CREATE ROLE banking_app
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'app_password';

REVOKE ALL ON DATABASE securebank FROM PUBLIC;

GRANT CONNECT ON DATABASE securebank
    TO banking_migrator, banking_app;

REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA banking AUTHORIZATION banking_migrator;

GRANT USAGE ON SCHEMA banking TO banking_app;

ALTER ROLE banking_migrator IN DATABASE securebank
    SET search_path TO banking;

ALTER ROLE banking_app IN DATABASE securebank
    SET search_path TO banking;

COMMIT;
