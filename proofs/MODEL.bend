import Base

# Per-slot, already-qualified inputs. 0=native, 1=inherited, 2=explicit lower,
# 3=selected custom, 4=built-in Default. Invalid branches are not qualified.
def source.lower(explicit: Bool, styled: Bool, inherited: Bool) -> Nat:
  match explicit:
    case True{}:
      2n
    case False{}:
      Bool.pick(Nat, styled, 4n, Bool.pick(Nat, inherited, 1n, 0n))

def source.custom(custom: Bool, explicit: Bool, styled: Bool, inherited: Bool) -> Nat:
  match custom:
    case True{}:
      3n
    case False{}:
      source.lower(explicit, styled, inherited)

def source(enabled: Bool, custom: Bool, explicit: Bool, styled: Bool, inherited: Bool) -> Nat:
  match enabled:
    case False{}:
      0n
    case True{}:
      source.custom(custom, explicit, styled, inherited)

type Resolution is Data:
  Resolution{requested: Nat, effective: Nat}

def resolve(requested: Nat, effective: Nat) -> Resolution:
  Resolution{requested, effective}

def requested(value: Resolution) -> Nat:
  Resolution{request, effective} = value
  request

def complete(inputs: Bool, graph: Bool, outputs: Bool) -> Bool:
  Bool.and(inputs, Bool.and(graph, outputs))

def publish(current: Nat, candidate: Nat, complete: Bool) -> Bool:
  Bool.and(Nat.is_eq(current, candidate), complete)

def package(own: Bool, preview: Bool, foreign: Bool) -> Bool:
  Bool.and(own, Bool.and(Bool.not(preview), Bool.not(foreign)))

def fresh(off: Bool, capable: Bool, source: Bool) -> Bool:
  Bool.and(Bool.not(off), Bool.and(capable, source))

# Default has model contributions only; artwork selection is a separate input.
def default_artwork() -> Nat:
  0n
