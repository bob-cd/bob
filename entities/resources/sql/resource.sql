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

-- :name insert-resource :insert :1
INSERT INTO "resources" ("name", "type", "pipeline", "provider")
VALUES (:name, :type, :pipeline, :provider);

-- :name insert-external-resource :insert :1
INSERT INTO "resource_providers" ("name", "url")
VALUES (:name, :url);

-- :name delete-external-resource :execute :1
DELETE FROM "resource_providers"
WHERE "name"=:name;

-- :name insert-resource-params :insert :n
INSERT INTO "resource_params" ("key", "value", "name", "pipeline")
VALUES :tuple*:params;
