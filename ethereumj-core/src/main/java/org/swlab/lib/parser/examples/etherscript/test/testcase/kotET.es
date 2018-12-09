account{balance:50ether} owner;
account{balance:50ether} user;
account{balance:50ether} hacker;

account{contract:"kotET.sol", by:owner, value:10ether} kotet("KingOfTheEtherThrone");
account{contract:"kotET.sol", by:hacker, value:12ether} victim("Victim", kotet);

kotet.(){by:user,value:11ether};

victim.claimThrone(){by:hacker};

assert owner.balance;
assert user.balance;
assert hacker.balance;
assert kotet.balance;
assert victim.balance;



