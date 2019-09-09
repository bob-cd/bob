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

-- :name register-artifact-store :insert :1
INSERT INTO "artifact_stores" ("name", "url")
VALUES (:name, :url);

-- :name get-artifact-store :query :1
SELECT "name", "url" FROM "artifact_stores"
WHERE "name"=:name;

-- :name un-register-artifact-store :execute :1
DELETE FROM "artifact_stores"
WHERE "name"=:name;

-- :name get-artifact-stores :query :many
SELECT * FROM "artifact_stores";
