(ns freeplane.script.fences
  "Pure version-fence logic: data in, data out. No Freeplane, no I/O."
  (:require [clojure.string :as str]))

(def supported
  "Three numbers per dependency: :floor (minimum supported), :built-against and :tested-up-to."
  {:clojure   {:floor "1.12.0" :built-against "1.12.2" :tested-up-to "1.12.2"}
   :freeplane {:floor "1.13.0" :built-against "1.13.4" :tested-up-to "1.13.4"}})

(def quirks
  "Known-version quirks table, empty in v1. Each entry has the shape
  {:id keyword
   :dependency :clojure|:freeplane
   :introduced \"v\"        ; inclusive
   :fixed-in \"v\"-or-nil   ; exclusive, nil means open-ended
   :disables #{op-keyword}
   :evidence \"test or issue\"}."
  [])

(def ^:private version-pattern
  #"^\s*(\d+(?:\.\d+)*)(?:[\s\-_.]*([A-Za-z].*?))?\s*$")

(defn parse-version
  "Parses a version string into {:numbers [major minor patch build] :qualifier q-or-nil}.
  Missing numbers are 0 and only the first four are kept. Returns nil when the
  string does not start with a number."
  [s]
  (when-let [[_ numbers qualifier] (and (string? s) (re-matches version-pattern s))]
    (let [parsed (mapv #(Long/parseLong %) (str/split numbers #"\."))]
      {:numbers   (vec (take 4 (concat parsed (repeat 0))))
       :qualifier qualifier})))

(defn- ->parsed
  [version]
  (cond
    (map? version) version
    (parse-version version) (parse-version version)
    :else (throw (IllegalArgumentException. (str "Unparsable version: " (pr-str version))))))

(defn compare-versions
  "Compares two versions given as strings or parsed maps. Numbers are compared
  first; with equal numbers any qualifier sorts before no qualifier."
  [a b]
  (let [{numbers-a :numbers qualifier-a :qualifier} (->parsed a)
        {numbers-b :numbers qualifier-b :qualifier} (->parsed b)
        by-numbers (compare numbers-a numbers-b)]
    (cond
      (not (zero? by-numbers)) by-numbers
      (and qualifier-a qualifier-b) (compare qualifier-a qualifier-b)
      qualifier-a -1
      qualifier-b 1
      :else 0)))

(defn at-least?
  "True when version found is the same as or newer than version needed."
  [found needed]
  (not (neg? (compare-versions found needed))))

(defn- violation
  [fence dependency found needed]
  (ex-info (str (name dependency) " " found " is older than the supported minimum " needed)
           {:fence fence :dependency dependency :found found :needed needed}))

(defn floor-violation
  "Returns nil when found meets the dependency's supported floor, else an ex-info
  (returned, not thrown) with data {:fence :floor :dependency :found :needed}."
  [dependency found]
  (let [floor (get-in supported [dependency :floor])]
    (when-not (at-least? found floor)
      (violation :floor dependency found floor))))

(defn requirement-violation
  "Returns nil when found meets a caller's declared minimum, else an ex-info
  (returned, not thrown) with data {:fence :requirement :dependency :found :needed}."
  [dependency found needed]
  (when-not (at-least? found needed)
    (violation :requirement dependency found needed)))

(defn newer-than-tested
  "Returns a warning string when found is newer than the dependency's tested-up-to
  version, else nil. Never a reason to block."
  [dependency found]
  (let [tested (get-in supported [dependency :tested-up-to])]
    (when (pos? (compare-versions found tested))
      (str (name dependency) " " found " is newer than the tested " tested))))

(defn- covers?
  [{:keys [introduced fixed-in]} version]
  (and (at-least? version introduced)
       (or (nil? fixed-in) (neg? (compare-versions version fixed-in)))))

(defn active-quirks
  "Returns the quirks for dependency whose range covers version. The range is
  inclusive :introduced and exclusive :fixed-in; a nil :fixed-in is open-ended."
  [quirks dependency version]
  (filterv #(and (= dependency (:dependency %)) (covers? % version)) quirks))

(defn disabled-violation
  "Returns nil when no active quirk disables op, else an ex-info (returned, not
  thrown) with data {:fence :quirk :dependency :found :op :id :evidence}."
  [quirks dependency version op]
  (when-let [{:keys [id evidence]} (->> (active-quirks quirks dependency version)
                                        (filter #(contains? (:disables %) op))
                                        first)]
    (ex-info (str (name dependency) " " version " disables " op " (" (name id) "): " evidence)
             {:fence :quirk :dependency dependency :found version :op op
              :id id :evidence evidence})))

(defn stale-quirks
  "Returns quirks whose :fixed-in is at or below their dependency's :floor, so the
  quirk can no longer apply and should be removed."
  [quirks supported]
  (filterv (fn [{:keys [dependency fixed-in]}]
             (and fixed-in
                  (at-least? (get-in supported [dependency :floor]) fixed-in)))
           quirks))
