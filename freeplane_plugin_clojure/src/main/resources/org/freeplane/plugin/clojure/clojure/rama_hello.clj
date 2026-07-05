(ns rama-hello
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.path :as path]))

;; 1. Define the module
;; We define a simple module with one depot (*in-depot) and one topology (s).
(defmodule MyModule [setup topologies]
  (rama/declare-depot setup *in-depot :random)
  (let [s (rama/stream-topology topologies "stream")]
    (rama/<<sources s
      (rama/source> *in-depot :> *v)
      (rama/println "Rama received value:" *v))))

(defn run-demo [node script-context]
  (println "🚀 Starting Rama IPC Demo...")
  
  ;; 2. Launch In-Process Cluster (IPC)
  ;; This starts a lightweight Rama cluster within the Freeplane process.
  (with-open [ipc (rama/launch-ipc! {:modules [MyModule]})]
    
    ;; 3. Get a handle to the depot
    (let [depot (rama/foreign-depot ipc (rama/get-module-name MyModule) "*in-depot")]
      
      ;; 4. Append data to the depot
      (println "Transacting data to Rama...")
      (rama/append! depot "Hello Freeplane from Rama!")
      
      ;; 5. Append data from the current node if available
      (when node
        (let [node-text (.getText node)]
           (rama/append! depot (str "From Node: " node-text))))

      ;; Wait briefly for async processing to complete (since IPC is async)
      (Thread/sleep 500)
      (println "✅ Demo finished."))))

;; Helper to run directly if loaded as a script
(when (find-ns 'rama-hello)
   ;; In Freeplane, scripts are wrapped, but we can export the main function
   ;; or just let the user call (rama-hello/run-demo node script-context)
   ;; For a simple "Run" action, we often just execute the logic.
   ;; However, since we defined a function, we should call it.
   ;; But Freeplane scripts usually execute top-level. 
   ;; Let's make it callable.
   (println "Rama Hello loaded. Call (rama-hello/run-demo node nil) to run.")
   
   ;; If we want it to run immediately upon menu selection without REPL interaction:
   ;; (run-demo node nil) ; Uncomment if strictly a one-shot script
   )
