(ns dropbox-sync-folder.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.walk :as w]))

(def downloadable-path "/hello")
(def config (->> "config.edn" io/resource slurp edn/read-string))
(def access-token (str "Bearer " (:access-token config)))

(defn download-file-request
  [path]
  (client/post "https://content.dropboxapi.com/2/files/download"
               {:headers {"Authorization" access-token
                          "Dropbox-API-Arg" (json/write-str {:path path})}
                :as :byte-array}))

(defn download-file
  [path]
  (with-open [out
              (let [file-path (str "./download" path)]
                (println (str "Downloading " file-path))

                (io/make-parents file-path)
                (io/output-stream (io/file file-path)))]
    (.write out (:body (download-file-request path)))))

(defn response
  [downloadable-path]

  (client/post "https://api.dropboxapi.com/2/files/list_folder"
               {:body (json/write-str {:path downloadable-path})
                :headers {"Authorization" access-token
                          "Content-Type" "application/json"}
                :content-type :json
                :accept :json}))

(defn rec
  [folder]

  (let [entries (->> (:body (response folder)) json/read-str w/keywordize-keys :entries)
        files-and-folders (group-by (comp keyword :.tag) entries)
        only-files (:file files-and-folders)
        only-folders (:folder files-and-folders)]
    (concat only-files (map #(rec (:path_display %)) only-folders))))

(defn download-all-files
  []
  (doseq
    [[file] (map list (flatten (rec downloadable-path)))]
    (download-file (:path_display file))))

(defn -main
  [& args]
  (download-all-files))
