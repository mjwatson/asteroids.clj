(ns asteroids.client
  (:require [clojure.browser.repl]))

;; Simple bouncing ball demo to get a feel for canvas and the repl
;; ***************************************************************

;; Global setting

(def GRAVITY 0.1)
(def gravity (atom GRAVITY))

(def ELASTICITY 0.8)
(def elasticity (atom ELASTICITY))

;; The canvas

(def canvas (-> js/document (.getElementById "canvas")))

(def context (.getContext canvas "2d"))

(def width (.-width canvas))

(def height (.-height canvas))

(def background "white")

(def default-foreground "red")

(def active-colour (atom default-foreground))

(defn split-colours [colour]
  (seq  (.split colour "-")))

;; The current state of the application
;; Stored in an atom for access via the repl

(defn make-animate [colours]
  (into {} (map vec (partition 2 1 colours colours))))

(defn make-ball [colour]
  (let [colours (split-colours colour)]
    { :context   context
     :generation 0
     :x          { :coord 0 :velocity 10 :max width  }
     :y          { :coord 0 :velocity 0  :max height }
     :d          40
     :foreground (first colours)
     :colours    (make-animate colours)}))

(defn make-state []
  {:generation 0
   :opacity    1
   :balls      {} })

(defn initial-state []
  (make-state))


;; History parameters controlling how many previous posistions are displayed

(def history-size    10)
(def history-freq    10)
(def history-queue   (* history-size history-freq))
(def history-opacity 0.05)

;; Draw the background and the ball on the canvas
;; Old states are drawn with increasing transparency

(defn setColor [context color opacity]
  (set! (.-fillStyle context) color)
  (set! (.-globalAlpha context) opacity))

(defn clear []
  (doto context
    (setColor background 1)
    (.fillRect  0 0 width height)))

(defn draw-ball
  ([ opacity {:keys [context x y d foreground]}]
     (doto context
       (setColor foreground opacity)
       .beginPath
       (.arc  (:coord x) (:coord y) d 0 (* 2 Math/PI) true)
       .closePath
       .fill )))

(defn draw-balls [state]
  (doseq [ball (-> state :balls vals)]
    (draw-ball (:opacity state) ball)))

(defn calculate-opacity [age]
  (/ 1 (inc (* history-opacity age))))

(defn draw-history [state history]
  (when history-size
    (doseq [old-state (take-nth history-freq history) :when old-state]
      (let [opacity (calculate-opacity (- (state :generation) (old-state :generation)))]
        (draw-balls (assoc old-state :opacity opacity))))))

(defn draw! [ball-history]
  (clear)
  (let [current-state (last ball-history)]
    (doto current-state
      (draw-history  ball-history)
      (draw-balls))))

;; Move the ball

(defn bounce [ {:keys [coord velocity max] :as dimension } ]
  (let [new-coord (+ coord velocity)]
    (if (and (< 0 new-coord max))
      (assoc dimension :coord    new-coord)
      (assoc dimension :velocity (* -1 @elasticity velocity)))))

(defn move-ball [state]
  (-> state
      (update-in [:x] bounce)
      (update-in [:y] bounce)))

(defn apply-gravity [state]
  (update-in state [:y :velocity] #(+ % @gravity)))

(defn age [state]
  (update-in state [:generation] inc))

(def animation-speed 10)

(defn animation-frame? [generation]
  (= 0 (mod generation animation-speed)))


(defn animate [state]
  (if (and (animation-frame? (:generation state)) (:colours state))
    (update-in state [:foreground] (state :colours))
    state))

(defn update-ball [ball]
  (-> ball
      age
      animate
      apply-gravity
      move-ball))

(defn update-values [d f]
  (into {} (for [[k v] d] [k (f v)])))

(defn update-balls [state]
  (update-in state [:balls] update-values update-ball))

(defn update-state [state]
  (-> state
      age
      update-balls))

;; Create an (almost) purely functional history as a sequence of the
;; last $history-size elements.
;;
;; Its not quite functional, as rather than just using iterate,
;; we store the last value of the state is stored in an atom
;; This allows it to be modified and viewed easily from the repl.
;; If this wasn't wanted then next-state could be just update-state.

(def state  (atom nil))
(def stream (atom nil))

(defn store-initial-state []
  (reset! state (initial-state)))

(defn next-state [& args]
  (swap! state update-state))

(defn state-stream []
  (iterate next-state (store-initial-state)))

(defn empty-history []
  (repeat history-queue nil))

(defn history-stream []
  (partition history-queue 1 (concat (empty-history) (state-stream))))

(defn init-state []
  (reset! stream (history-stream)))

;; Useful function for playing at the repl

(defn shove
  ([x y]
     (shove x y default-foreground))
  ([x y colour]
     (when (-> @state :balls (contains? colour))
       (swap! state
              (fn [s]
                (-> s
                    (update-in [:balls colour :x :velocity] #(+ % x))
                    (update-in [:balls colour :y :velocity] #(+ % y))))))))

(defn shove-all! [x y]
  (doseq [ c (-> @state :balls keys)]
    (shove x y c)))

(defn fire! [colour]
  (swap! state update-in [:balls] assoc colour (make-ball colour)))

(defn blat! [colour]
  (swap! state update-in [:balls] dissoc colour))

;; Allow arrow keys to shove the ball
(def key-mappings
  {37 [-10 0]
   38 [0 -10]
   39 [10  0]
   40 [0  10]})

(defn on-keydown [e]
  (when-let [[x y] (key-mappings (.-keyCode e))]
    (.preventDefault e)
    (shove x y @active-colour)
    false))

(defn register-for-key-events []
  (.addEventListener js/window "keydown" on-keydown))

;; Top level control
;; Includes functions to start/stop/restart from the repl

(def running (atom false))

(defn tick []
  (swap! stream rest)
  (draw! (first @stream)))

(def requestAnimation (or (.-requestAnimationFrom js/window)
                          (.-webkitRequestAnimationFrame js/window)
                          (.-mozRequestAnimationFrom js/window)
                          (fn [c] (.setTimeout js/window c (/ 1000 60)))))

(defn run []
  (requestAnimation run)
  (when @running
    (tick)))

(defn start [& _]
  (reset! running true))

(defn stop []
  (reset! running false))

(defn restart []
  (stop)
  (init-state)
  (fire! @active-colour)
  (start))

;; Allow control of application from ui



(defn get-mouse-event [id handler]
  (.addEventListener (.getElementById js/document id) "mousedown" handler))

(defn set-text [id text]
  (set! (.-innerHTML (.getElementById js/document id)) text))

(defn mouse-update-atom [name id a update]
  (let [button-id (str name "_" id)
        label-id  (str name "_" "reset")]
    (get-mouse-event button-id
                     (fn []
                       (swap! a update)
                       (set-text label-id (str name " " (.toFixed @a 2)))))))

(defn mouse-change-atom [name id a v]
  (mouse-update-atom name id a (partial + v)))

(defn mouse-reset-atom [name id a v]
  (mouse-update-atom name id a (fn [_] v)))

(defn register-for-mouse-events []
  (get-mouse-event   "stop"       stop)
  (get-mouse-event   "start"      start)
  (get-mouse-event   "restart"    restart)
  (mouse-change-atom "gravity"    "plus"  gravity    0.02)
  (mouse-reset-atom  "gravity"    "reset" gravity    GRAVITY)
  (mouse-change-atom "gravity"    "minus" gravity    -0.02)
  (mouse-change-atom "elasticity" "plus"  elasticity 0.05)
  (mouse-reset-atom  "elasticity" "reset" elasticity ELASTICITY)
  (mouse-change-atom "elasticity" "minus" elasticity -0.05))



(defn get-colour-elements [selector]
  (let [elements (-> js/document (.querySelectorAll selector))]
    (for [n (range (.-length elements))]
      (aget elements n))))

(defn on-fire []
  (if (-> @state :balls (contains? @active-colour))
    (blat! @active-colour)
    (fire! @active-colour)))

(defn party! []
  (init-state)
  (doseq [ [n element] (map vector  (range) (take 6 (get-colour-elements ".colour"))) ]
    (let [colour (.-id element)]
      (.setTimeout js/window
                   (fn [] (fire! colour) (shove 0 (* -1 n 2) colour))
                   (* n 2000)))))

(defn make-style [colour]11
  (let [colours (split-colours colour)]
    (if (= 1 (count colours))
      (str "background-color: " colour)
      (str "background: linear-gradient(90deg, " (first colours) " 50%, " (second  colours) " 50%)"))))

(defn register-ball-controls []
  (get-mouse-event "fire!" on-fire)
  (get-mouse-event "party!" party!)
  (doseq [element (get-colour-elements ".colour")]
    (let [colour (.-id element)]
      (set! (.-style element) (make-style colour))
      (get-mouse-event colour (fn [] (reset! active-colour colour))))))

;; Initialise the whole she-bang.

(defn ^:export init []
  (register-for-key-events)
  (register-for-mouse-events)
  (register-ball-controls)
  (restart)
  (run))
