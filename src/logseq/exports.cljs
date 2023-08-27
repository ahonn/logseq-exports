(ns logseq.exports
  "Exports SPA publishing app"
  (:require [logseq.graph-parser.cli :as gp-cli]
            [logseq.publishing :as publishing]
            ["fs" :as fs]
            ["path" :as node-path]
            [datascript.core :as d]
            [babashka.cli :as cli]
            [clojure.edn :as edn]))

(defn- get-db [graph-dir]
  (let [{:keys [conn]} (gp-cli/parse-graph graph-dir {:verbose false})] @conn))

(def ^:private spec
  "Options spec"
  {:directory {:desc "Graph directory to export"
               :alias :d
               :default "."}
   :help {:alias :h
          :desc "Print help"}
   :static-directory {:desc "Logseq's static directory"
                      :alias :s
                      :default "../logseq/static"}})

(defn- validate-directories [graph-dir static-dir]
  (when-not (fs/existsSync (node-path/join graph-dir "logseq" "config.edn"))
    (println (str "Error: Invalid graph directory '" graph-dir
                  "' as it has no logseq/config.edn."))
    (js/process.exit 1))
  (when-not (fs/existsSync static-dir)
    (println (str "Error: Logseq static directory '" static-dir
                  "' does not exist. Please provide a valid directory"))
    (js/process.exit 1)))

(defn- get-public-pages
  [conn]
  (d/q
    '[:find (pull ?page [:block/name :block/properties :block/journal?]) (pull ?file [*])
      :where
       [?page :block/name ?page-name]
       [?page :block/properties ?properties]
       [(get ?properties :public) ?public]
       [(= true ?public)]
       [?page :block/file ?file]]
    conn))

(defn ^:api -main
  [& args]
  (let [options (cli/parse-opts args {:spec spec})
        _ (when (or (:help options) (= 0 (count args)))
            (println (str "Usage: logseq-publish-spa OUT-DIR [OPTIONS]\nOptions:\n"
                          (cli/format-opts {:spec spec})))
            (js/process.exit 1))
        _ (when js/process.env.CI (println "Options:" (pr-str options)))
        [static-dir graph-dir output-path]
        ;; Offset relative paths for CI since it is run in a different dir
        (map #(if js/process.env.CI (node-path/resolve ".." %) %)
             [(:static-directory options) (:directory options) (first args)])
        _ (validate-directories graph-dir static-dir)
        repo-config (-> (node-path/join graph-dir "logseq" "config.edn") fs/readFileSync str edn/read-string)
        public-pages (get-public-pages (get-db graph-dir))]
      (js/console.log (clj->js public-pages))))


#js {:main -main}
