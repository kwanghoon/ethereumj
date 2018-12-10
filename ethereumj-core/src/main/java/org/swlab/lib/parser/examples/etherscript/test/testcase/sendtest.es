account{balance:50ether} owner;
account{balance:50ether} user1;
account{balance:50ether} user2;
account{balance:50ether} user3;

account{contract:"sendtest.sol", by:owner} sendtest("SendTest");
account{contract:"sendtest.sol", by:user1} recipient1("D1");
account{contract:"sendtest.sol", by:user2} recipient2("D2");
account{contract:"sendtest.sol", by:user3} recipient3("D3");

sendtest.(){by:owner, value:10ether};

sendtest.pay(1ether, recipient1){by:owner};
sendtest.pay(1ether, recipient2){by:owner};
sendtest.pay(1ether, recipient3){by:owner};

assert owner.balance;
assert recipient1.balance;
assert recipient2.balance;
assert recipient3.balance;
