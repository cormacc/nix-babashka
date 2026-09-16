#!/usr/bin/env bb
;; Rewrite nix/pins.json from upstream babashka release + dev-build sidecars.
;; No tarball downloads: only the GitHub releases API and .sha256 sidecars.
(ns bump
  (:require
   [babashka.http-client :as http]
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.string :as str]))

(def pins-path "nix/pins.json")

;; system -> upstream asset suffix, shared with nix/bin.nix.
(def system->suffix
  (sorted-map
   "aarch64-darwin" "macos-aarch64"
   "aarch64-linux" "linux-aarch64-static"
   "x86_64-darwin" "macos-amd64"
   "x86_64-linux" "linux-amd64"))

(def settling-minutes 30)

(defn- auth-headers []
  (if-let [token (System/getenv "GITHUB_TOKEN")]
    {"Authorization" (str "Bearer " token)}
    {}))

(defn- fetch!
  "GET url, returning the body string. Exits 1 naming the url on non-2xx."
  [url]
  (let [resp (http/get url {:headers (auth-headers) :throw false})]
    (if (<= 200 (:status resp) 299)
      (:body resp)
      (do
        (binding [*out* *err*]
          (println (str "error: GET " url " -> HTTP " (:status resp))))
        (System/exit 1)))))

(defn- fetch-json! [url]
  (json/parse-string (fetch! url) true))

(defn- sri-hash [hex]
  (-> (shell {:out :string} "nix" "hash" "convert" "--hash-algo" "sha256" "--to" "sri" hex)
      :out
      str/trim))

(defn- asset-url [repo version suffix]
  (str "https://github.com/babashka/" repo "/releases/download/v" version
       "/babashka-" version "-" suffix ".tar.gz"))

(defn- fetch-hashes!
  "Fetch the four platform sidecars for repo/version. All four or none: any
  failure exits 1 via fetch!, so a partial hashes map is never returned."
  [repo version]
  (into (sorted-map)
        (for [[system suffix] system->suffix]
          ;; keyword keys: json/parse-string keywordizes nix/pins.json's own
          ;; system-name keys, and pins/pins' must compare and merge cleanly.
          [(keyword system) (sri-hash (str/trim (fetch! (str (asset-url repo version suffix) ".sha256"))))])))

(defn- bump-release [pins]
  (let [latest (fetch-json! "https://api.github.com/repos/babashka/babashka/releases/latest")
        version (subs (:tag_name latest) 1)]
    (if (= version (get-in pins [:release :version]))
      (do (println "release: unchanged")
          pins)
      (let [hashes (fetch-hashes! "babashka" version)]
        (println (str "release: " (get-in pins [:release :version]) " -> " version))
        (assoc pins :release (sorted-map :hashes hashes :version version))))))

(defn- too-recent? [updated-at]
  (< (.toMinutes (java.time.Duration/between (java.time.Instant/parse updated-at) (java.time.Instant/now)))
     settling-minutes))

(defn- settling? [assets version]
  (let [by-name (into {} (map (juxt :name identity)) assets)]
    (some (fn [[_system suffix]]
            (let [name (str "babashka-" version "-" suffix ".tar.gz")]
              (if-let [asset (get by-name name)]
                (too-recent? (:updated_at asset))
                (do
                  (binding [*out* *err*]
                    (println (str "error: babashka-dev-builds release v" version " is missing asset " name)))
                  (System/exit 1)))))
          system->suffix)))

(defn- bump-snapshot [pins]
  (let [releases (fetch-json! "https://api.github.com/repos/babashka/babashka-dev-builds/releases?per_page=1")
        latest (first releases)
        version (subs (:tag_name latest) 1)]
    (if (settling? (:assets latest) version)
      (do (println "snapshot: skipped (settling)")
          pins)
      (let [hashes (fetch-hashes! "babashka-dev-builds" version)]
        (if (= hashes (get-in pins [:snapshot :hashes]))
          (do (println "snapshot: unchanged")
              pins)
          (let [today (str (java.time.LocalDate/now java.time.ZoneOffset/UTC))]
            (println (str "snapshot: updated " today))
            (assoc pins :snapshot (sorted-map :hashes hashes :updated today :version version))))))))

(defn- sort-keys
  "Recursively coerce every map into a sorted-map so cheshire always emits
  keys in sorted order, regardless of which branch touched them."
  [x]
  (if (map? x)
    (into (sorted-map) (map (fn [[k v]] [k (sort-keys v)])) x)
    x))

(defn- write-pretty-json! [path data]
  (spit path (str (json/generate-string (sort-keys data) {:pretty true}) "\n")))

(let [dry-run? (some #{"--dry-run"} *command-line-args*)
      pins (json/parse-string (slurp pins-path) true)
      pins' (-> pins bump-release bump-snapshot)]
  (when (and (not dry-run?) (not= pins pins'))
    (write-pretty-json! pins-path pins')))
