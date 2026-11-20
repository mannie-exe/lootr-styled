import Std

namespace LootrStyled

-- Mathematical obligations for the subtraction-first byte gate. Host adapters
-- additionally check negative ints, conversion bounds and individual file sizes.
def consume (used count limit : Nat) : Option Nat :=
  if used ≤ limit ∧ count ≤ limit - used then some (used + count) else none

theorem accepted_is_bounded (used count limit result : Nat)
    (h : consume used count limit = some result) : result ≤ limit := by
  simp only [consume] at h
  split at h
  next conditions =>
    simp only [Option.some.injEq] at h
    omega
  next => contradiction

theorem rejected_retains_no_partial_result (used count limit : Nat)
    (h : limit < used + count) : consume used count limit = none := by
  simp only [consume]
  split
  next conditions => omega
  next => rfl

theorem limit_fits_java_int : (16 * 1024 * 1024 : Nat) < 2^31 := by decide

-- A publication receipt identifies one current, complete generation; equality
-- of a schema/version number alone is insufficient.
def publish (current candidate : Nat) (complete : Bool) : Bool :=
  current == candidate && complete

theorem stale_refused (current candidate : Nat) (complete : Bool)
    (h : current ≠ candidate) : publish current candidate complete = false := by
  simp [publish, h]

end LootrStyled
