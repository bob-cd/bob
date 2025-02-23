; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(require
 '[babashka.process :as p]
 '[clj-yaml.core :as yaml]
 '[clojure.string :as str])

(defn run
  [cmd-name cmd]
  {:run {:name cmd-name
         :command cmd}})

(defn gen-steps
  [shorted? steps]
  (if shorted?
    [(run "Shorted" "echo 'Skipping Run'")]
    steps))

(defn gen-job
  [shorted? conf]
  (if shorted?
    (-> conf
        (dissoc :machine)
        (assoc :resource_class "small" :docker [{:image "ubuntu:latest"}]))
    conf))

(def java-home "/tmp/jdk")

(def java-download-url "https://download.oracle.com/java/23/latest/jdk-23_linux-x64_bin.tar.gz")

(def vm-image "ubuntu-2404:current")

(def common-steps
  [:checkout
   (run "Git clean slate" "rm -rf ~/.gitconfig")
   (run "Setup Babashka"
        "curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
        sudo bash install")
   (run
    "Setup Java"
    (format
     "wget -O jdk.tar.gz %s
     mkdir %s
     tar -zxf jdk.tar.gz -C %s --strip-components=1"
     java-download-url
     java-home
     java-home))
   (run "Setup Clojure"
        "curl -sLO https://download.clojure.org/install/linux-install.sh
        sudo bash linux-install.sh")
   {:restore_cache {:keys "bob-v1-"}}
   (run "Prep all deps" "bb prep")])

(defn build
  [shorted?]
  (gen-job
   shorted?
   {:machine {:image vm-image}
    :resource_class "large"
    :environment {:JAVA_HOME java-home
                  :JAVA_CMD (str java-home "/bin/java")}
    :steps
    (gen-steps
     shorted?
     (concat
      common-steps
      [(run "Run all tests" "bb test")
       {:save_cache
        {:key
         "bob-v1-{{ checksum \"apiserver/deps.edn\" }}-{{ checksum \"runner/deps.edn\" }}-{{ checksum \"common/deps.edn\" }}"
         :paths ["~/.m2"
                 "~/.gitlibs"]}}]))}))

(defn deploy
  [shorted?]
  (gen-job
   shorted?
   {:machine {:image vm-image}
    :resource_class "large"
    :environment {:JAVA_HOME java-home
                  :JAVA_CMD (str java-home "/bin/java")}
    :steps
    (gen-steps
     shorted?
     (concat
      common-steps
      [(run "Build executables" "bb compile")
       (run "Create multi-platform capabale buildx builder"
            "docker run --privileged --rm tonistiigi/binfmt --install all
           docker buildx create --use")
       (run "Docker login" "echo ${GHCR_TOKEN} | docker login ghcr.io --username lispyclouds --password-stdin")
       (run "Build and publish images" "bb image")]))}))

(defn make-config
  [shorted?]
  {:version 2.1
   :jobs {:build (build shorted?)
          :deploy (deploy shorted?)}
   :workflows {:version 2
               :ci {:jobs ["build"
                           {:deploy {:filters {:branches {:only "main"}}
                                     :requires ["build"]}}]}}})

(def skip-config
  {:skip-if-only [#".*.md$"
                  #"LICENSE"]})

(defn get-changes
  []
  (-> (p/shell {:out :string} "git diff --name-only HEAD~1")
      (:out)
      (str/split-lines)))

(defn irrelevant-change?
  [change regexes]
  (some? (some #(re-matches % change) regexes)))

(defn relevant?
  [change-set regexes]
  (some? (some #(not (irrelevant-change? % regexes)) change-set)))

(defn main
  []
  (let [{:keys [skip-if-only]} skip-config
        changed-files (get-changes)
        conf (make-config (not (relevant? changed-files skip-if-only)))]
    (println (yaml/generate-string conf
                                   :dumper-options
                                   {:flow-style :block}))))

(when (= *file* (System/getProperty "babashka.file"))
  (main))
