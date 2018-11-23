# state-monad
Example usage of state monad from cats and comparison with implementation of logic in actors

## Model

This application is modeling vending machine. Program interacts with user by simple command in terminal. User can:
 - insert coin
 - select product
 - withdraw money

Vending machine can give you a product, change or display message. Additionally vending machine is sending reports to owner like:
 - there is a lot of money
 - issues with expiry date
 - vending machine run out of product

## Implementation
There are two implementations of vending machine:
 - [actor based](https://github.com/otrebski/state-monad/blob/master/src/main/scala/vending/BaseVendingMachineActor.scala), all logic is in actor
 - [all logic in state monad](https://github.com/otrebski/state-monad/blob/master/src/main/scala/vending/VendingMachineSm.scala) (from cats), [actor](https://github.com/otrebski/state-monad/blob/master/src/main/scala/vending/SmActor.scala) is for communication and keeping state 

## Run app
Running application: 

```bash
sbt "runMain vending.VendingMachineDemo"
```

You will be asked to choose implementation.

Interaction with vending machine:
```
+digit -> insert coins, for example: +3
digit  -> select number, for example: 2
-      -> resign and take your money
q      -> quit program
```

## Tests

Tests are written for:
 - [actors](https://github.com/otrebski/state-monad/blob/master/src/test/scala/vending/BaseActorTest.scala)
 - [state monad](https://github.com/otrebski/state-monad/blob/master/src/test/scala/vending/VendingMachineSmTest.scala)

One test is ignored (`should do not report if money box is almost full  for a second time`). This test is currently failing because feature is not implemented. You can enable this test and implement logic using Actor and state monad.