ALTER TABLE incident_phone_sessions ADD COLUMN waiting_expires_at TIMESTAMP WITH TIME ZONE;

-- Serialize intake preparation/renewal/claim for the single inbound number,
-- including concurrent preparation when no waiting row exists yet.
CREATE TABLE phone_dispatch_lock (id INTEGER PRIMARY KEY);
INSERT INTO phone_dispatch_lock (id) VALUES (1);
