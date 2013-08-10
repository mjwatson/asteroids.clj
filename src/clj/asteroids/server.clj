(ns asteroids.server
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.core :refer (GET defroutes)]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [clojure.java.io :as io])
  (:gen-class))

;; Horrible hack needed to make the page connect to the austin brepl.
;; Basically appends the necessary javascript to the end of the main page

(enlive/deftemplate page
  "public/index.html"
  []
  [:body] (enlive/append
            (enlive/html 
              [:script (browser-connected-repl-js)])))

;; The routes - pretty simple for now.

(defroutes app-routes
  (GET "/" [] (response/redirect "index.html"))
  (GET "/index.html" req (page))
  (route/resources "/")
  (route/not-found (str "Page not found" "!")))

;; Start the server via lein run, lein ring server or from a repl.

(def handler
  (handler/site app-routes))

(defn start [{:keys [start join?] :as options}]
  (let [ server (jetty/run-jetty #'app-routes options)]
    server))

(defn run []
  (start {:port 8080 :join? false}))

(defn -main [& args]
  (start {:port 8080 :join? true}))

