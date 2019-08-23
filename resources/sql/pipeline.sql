-- This file is part of Bob.

-- Bob is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.

-- Bob is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU General Public License for more details.

-- You should have received a copy of the GNU General Public License
-- along with Bob. If not, see <http://www.gnu.org/licenses/>.

-- :name insert-pipeline :insert :1
INSERT INTO "pipelines" ("name", "image")
VALUES (:name, :image);

-- :name insert-evars :insert :n
INSERT INTO "evars" ("key", "value", "pipeline")
VALUES :tuple*:evars;

-- :name insert-step :insert :1
INSERT INTO "steps" ("cmd", "needs_resource", "produces_artifact", "artifact_path", "pipeline")
VALUES (:cmd, :needs_resource, :produces_artifact, :artifact_path, :pipeline);

-- :name delete-pipeline :execute :1
DELETE FROM "pipelines"
WHERE "name"=:name;

-- :name ordered-steps :query :many
SELECT * FROM "steps"
WHERE "pipeline"=:pipeline
ORDER BY "id";

-- :name evars-by-pipeline :query :many
SELECT "key", "value" FROM "evars"
WHERE "pipeline"=:pipeline;

-- :name status-of :query :1
SELECT "status" FROM "runs"
WHERE "pipeline"=:pipeline AND "number"=:number
LIMIT 1;

-- :name delete-pipeline :execute :n
DELETE FROM "pipelines"
WHERE "name"=:name;

-- :name update-runs :execute :1
UPDATE "runs"
SET "last_pid"=:pid
WHERE "id"=:id;

-- :name run-stopped? :query :1
SELECT "stopped" FROM "runs"
WHERE "id"=:id;

-- :name pipeline-runs :query :many
SELECT * FROM "runs"
WHERE "pipeline"=:pipeline;

-- :name insert-run :insert :1
INSERT INTO "runs" ("id", "number", "pipeline", "status")
VALUES (:id, :number, :pipeline, :status);

-- :name update-run :execute :1
UPDATE "runs"
SET "status"=:status
WHERE "id"=:id;

-- :name stop-run :execute :1
UPDATE "runs"
SET "stopped"=TRUE
WHERE "pipeline"=:pipeline AND "number"=:number;

-- :name pid-of-run :query :1
SELECT "last_pid" FROM "runs"
WHERE "pipeline"=:pipeline AND "number"=:number;

-- :name run-id-of :query :1
SELECT "id" FROM "runs"
WHERE "pipeline"=:pipeline AND "number"=:number;

-- :name image-of :query :1
SELECT "image" FROM "pipelines"
WHERE "name"=:name;

-- :name upsert-log :insert :1
INSERT INTO "logs" ("run", "content")
VALUES (:run, :content)
ON CONFLICT ("run")
DO UPDATE SET "content" = "logs"."content" || E'\n' || EXCLUDED.content;

-- :name logs-of :query :1
SELECT "content" FROM "logs"
WHERE "run"=:run-id;
