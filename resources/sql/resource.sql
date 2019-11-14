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

-- :name insert-resource :insert :1
INSERT INTO "resources" ("name", "type", "pipeline", "provider")
VALUES (:name, :type, :pipeline, :provider);

-- :name insert-external-resource :insert :1
INSERT INTO "resource_providers" ("name", "url")
VALUES (:name, :url);

-- :name delete-external-resource :execute :1
DELETE FROM "resource_providers"
WHERE "name"=:name;

-- :name get-external-resources :query :many
SELECT * FROM "resource_providers";

-- :name resource-by-pipeline :query :1
SELECT * FROM "resources"
WHERE "name"=:name AND "pipeline"=:pipeline
LIMIT 1;

-- :name external-resource-url :query :1
SELECT "url" FROM "resource_providers"
WHERE "name"=:name;

-- :name resource-params-of :query :many
SELECT "key", "value" FROM "resource_params"
WHERE "name"=:name AND "pipeline"=:pipeline;

-- :name insert-resource-params :insert :n
INSERT INTO "resource_params" ("key", "value", "name", "pipeline")
VALUES :tuple*:params;

-- :name invalid-external-resources :query :many
SELECT * FROM "resource_providers"
WHERE "name"=:name AND "url" IS NOT NULL;

-- :name resources-by-pipeline :query :many
SELECT * FROM "resources"
WHERE "pipeline"=:pipeline;

