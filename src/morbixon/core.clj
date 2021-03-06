(ns morbixon.core
  (:gen-class)
  (:use [compojure.core]
        [clojure-commons.error-codes]
        [morbixon.json-body :only [parse-json-body]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [ring.middleware.params]
        [ring.middleware.keyword-params]
        [ring.middleware.nested-params]
        [ring.middleware.multipart-params]
        [ring.middleware.cookies]
        [ring.middleware.session]
        [ring.middleware.stacktrace]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [morbixon.config :as cfg]
            [morbixon.controllers :as controllers]
            [clojure.tools.cli :as cli]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]))

(defroutes morbixon-routes
  (GET "/" [] "Morbixon says hi.")
  (POST "/" request (trap "provenance" controllers/do-provenance request)))

(defn site-handler [routes]
  (-> routes
      parse-json-body
      wrap-multipart-params
      wrap-keyword-params
      wrap-nested-params
      wrap-query-params
      wrap-stacktrace))

(defn parse-args
  [args]
  (cli/cli
   args
   ["-c" "--config"
    "Set the local config file to read from. Bypasses Zookeeper."
    :default nil]
   ["-h" "--help"
    "Show the help."
    :default false
    :flag true]
   ["-p" "--port"
    "Set the port to listen on."
    :default 31305
    :parse-fn #(Integer. %)]))

(def app
  (site-handler morbixon-routes))

(defn -main
  [& args]

  (let [[opts args help-str] (parse-args args)]
    (cond
     (:help opts)
     (do (println help-str)
         (System/exit 0)))

    (if (:config opts)
      (cfg/local-init (:config opts))
      (cfg/zk-init))

    (jetty/run-jetty app {:port (or (:port opts) (cfg/local-init))})))
