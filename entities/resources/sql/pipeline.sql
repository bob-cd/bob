-- This file is part of Bob.

-- Bob is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.

-- Bob is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU Affero General Public License for more details.

-- You should have received a copy of the GNU Affero General Public License
-- along with Bob. If not, see <http://www.gnu.org/licenses/>.

-- :name insert-pipeline :insert :1
INSERT INTO "pipelines" ("name", "image")
VALUES (:name, :image);

-- :name insert-evars :insert :n
INSERT INTO "evars" ("key", "value", "pipeline")
VALUES :tuple*:evars;

-- :name insert-step :insert :1
INSERT INTO "steps" ("cmd", "needs_resource", "produces_artifact", "artifact_path", "artifact_store", "pipeline")
VALUES (:cmd, :needs_resource, :produces_artifact, :artifact_path, :artifact_store, :pipeline);

-- :name delete-pipeline :execute :1
DELETE FROM "pipelines"
WHERE "name"=:name;

-- :name delete-pipeline :execute :n
DELETE FROM "pipelines"
WHERE "name"=:name;

-- :name insert-resource :insert :1
INSERT INTO "resources" ("name", "type", "pipeline", "provider")
VALUES (:name, :type, :pipeline, :provider);

-- :name insert-resource-params :insert :n
INSERT INTO "resource_params" ("key", "value", "name", "pipeline")
VALUES :tuple*:params;
