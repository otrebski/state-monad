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
 - actor based, all logic is in actor
 - all logic in state monad (from cats), actor is for communication and keeping state 

## Run app
Running application: 

```bash
sbt runMain vending.VendingMachineDemo
```

