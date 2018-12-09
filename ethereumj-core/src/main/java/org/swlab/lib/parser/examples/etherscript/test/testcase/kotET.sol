pragma solidity ^0.4.0;

contract KingOfTheEtherThrone {
    address public king;
    uint public claimPrice;// = 10 ether;
    address owner;
    uint public balance;
    //constructor, assigning ownership
    constructor() payable {
        owner = msg.sender;
        king = msg.sender;
        claimPrice = msg.value;
    }
    //for contract creator to withdraw commission fees
    function sweepCommission(uint amount) {
        owner.send(amount);
    }
    //fallback function
    function() payable {
        if (msg.value < claimPrice) revert();
        uint compensation = calculateCompensation();

        king.send(compensation);
        king = msg.sender;
        claimPrice = msg.value; //calculateNewPrice();
    }
    function calculateCompensation() returns (uint) {
        return (claimPrice - 1 ether);
    }
    function getBal() public {
        balance = address(this).balance;
    }
}

contract Victim {
	KingOfTheEtherThrone public kotET;
	uint public balance;
	function Victim(KingOfTheEtherThrone _kotET) payable {
		kotET = _kotET;
// 		balance = address(this).balance;
	}
	function() {
	    throw;
	}
	function claimThrone() public {
		kotET.send(address(this).balance);
// 		kotET.call.value(address(this).balance);
	}
	function getBal() public {
        balance = address(this).balance;
    }
}