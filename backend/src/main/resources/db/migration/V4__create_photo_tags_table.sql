-- V4: Create photo_tags table for organizing photos
-- Tags allow users to categorize and search their photos

CREATE TABLE photo_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    tag_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Prevent duplicate tags on same photo
    CONSTRAINT uq_photo_tag UNIQUE (photo_id, tag_name)
);

-- Indexes for performance
CREATE INDEX idx_photo_tags_photo_id ON photo_tags(photo_id);
CREATE INDEX idx_photo_tags_tag_name ON photo_tags(tag_name);
CREATE INDEX idx_photo_tags_created_at ON photo_tags(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE photo_tags IS 'Tags for categorizing and organizing photos';
COMMENT ON COLUMN photo_tags.tag_name IS 'Tag name (e.g., "vacation", "family", "work")';
COMMENT ON CONSTRAINT uq_photo_tag ON photo_tags IS 'Prevent duplicate tags on the same photo';
