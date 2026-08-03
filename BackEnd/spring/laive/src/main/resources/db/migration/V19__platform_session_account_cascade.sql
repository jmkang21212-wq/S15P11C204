-- A platform account is soft-disconnected while historical broadcasts exist.
-- A hard delete only occurs as part of streamer deletion, where the whole
-- broadcast graph must be removable without an FK cascade ordering conflict.
ALTER TABLE broadcast_platform_sessions
    DROP CONSTRAINT broadcast_platform_sessions_platform_account_id_fkey,
    ADD CONSTRAINT fk_broadcast_platform_sessions_platform_account
        FOREIGN KEY (platform_account_id)
        REFERENCES platform_accounts (id) ON DELETE CASCADE;

-- Keep the legacy primary account column and the expand-phase join table in
-- sync. New application versions replace the complete assignment set after
-- this trigger mirrors the primary account, while old versions remain safe.
CREATE OR REPLACE FUNCTION sync_calendar_event_primary_platform_account()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.platform_account_id IS DISTINCT FROM NEW.platform_account_id
       AND OLD.platform_account_id IS NOT NULL THEN
        DELETE FROM calendar_event_platform_accounts
         WHERE calendar_event_id = NEW.id
           AND platform_account_id = OLD.platform_account_id;
    END IF;

    IF NEW.platform_account_id IS NOT NULL THEN
        INSERT INTO calendar_event_platform_accounts (
            calendar_event_id,
            platform_account_id
        ) VALUES (
            NEW.id,
            NEW.platform_account_id
        )
        ON CONFLICT DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_calendar_event_primary_platform_account
AFTER INSERT OR UPDATE OF platform_account_id ON calendar_events
FOR EACH ROW
EXECUTE FUNCTION sync_calendar_event_primary_platform_account();
