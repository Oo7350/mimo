-- V7: Add team_id to calendar_events for team visibility
ALTER TABLE calendar_events ADD COLUMN team_id BIGINT NULL AFTER user_id;
CREATE INDEX idx_calendar_events_team_id ON calendar_events(team_id);
