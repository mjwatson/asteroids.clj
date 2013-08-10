(ns hello-clojurescript
  (:require [clojure.browser.repl]))

(defn whoami []
  (.-userAgent js/navigator))

(defn handle-click []
  (js/alert (whoami)))

(def clickable 
  (.getElementById js/document "clickable"))

(.addEventListener clickable "click" handle-click)
