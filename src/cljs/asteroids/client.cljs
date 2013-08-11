(ns asteroids.client
  (:require [clojure.browser.repl]))

;; Simple bouncing ball demo to get a feel for canvas and the repl
;; ***************************************************************

;; Global setting

(def gravity 0.1)

(def elasticity 0.80)

;; The canvas

(defn canvas []
  (-> js/document (.getElementById "canvas")))

(defn context [canvas]
  (.getContext canvas "2d"))

(defn width [canvas]
  (.-width canvas))

(defn height [canvas]
  (.-height canvas))

;; The state of the application

(def state (atom {}))

(defn init-state []
  (let [canvas (canvas)]
    (reset! state {:context    (context canvas)
                   :x          { :coord 0 :velocity 10 :max (width  canvas)}
                   :y          { :coord 0 :velocity 0  :max (height canvas)}
                   :d          40
                   :foreground "red"
                   :background "white" })))

;; Draw the background and the ball on the canvas

(defn setColor [context color]
  (set! (.-fillStyle context) color))

(defn clear [{:keys [context x y background]}]
  (doto context
    (setColor  background)
    (.fillRect 0 0 (:max x) (:max y))))

(defn drawBall [{:keys [context x y d foreground]}]
  (doto context
    (setColor foreground)
    .beginPath 
    (.arc  (:coord x) (:coord y) d 0 (* 2 Math/PI) true)
    .closePath 
    .fill ))

(defn draw! [state]
  (doto state 
    clear
    drawBall))

;; Move the ball

(defn bounce [ {:keys [coord velocity max] :as dimension } ]
  (let [new-coord (+ coord velocity)]
    (if (and (< 0 new-coord) (< new-coord max))
      (assoc dimension :coord    new-coord)
      (assoc dimension :velocity (* -1 elasticity velocity)))))

(defn move-ball [state]
  (-> state
      (update-in [:x] bounce)
      (update-in [:y] bounce)))

(defn apply-gravity [state]
  (update-in state [:y :velocity] #(+ % gravity)))

(defn update-state [state]
  (-> state
      apply-gravity
      move-ball))

;; Top level control
;; Includes functions to start/stop/restart from the repl

(def running (atom true))

(defn tick []
  (swap! state update-state)
  (draw! @state))

(defn run []
  (when @running
    (.mozRequestAnimationFrame js/window run)
    (tick)))

(defn start []
  (reset! running true)
  (run))

(defn restart []
  (stop)
  (init-state)
  (start))

(defn stop []
  (reset! running false))

(defn init []
  (restart))

;; Useful function for playing at the repl

(defn shove [x y]
  (swap! state
    (fn [s]
      (-> s
          (update-in [:x :velocity] #(+ % x))
          (update-in [:y :velocity] #(+ % y))))))

