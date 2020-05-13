var exec = require('cordova/exec');

exports.hello = function (success, error) {
    exec(success, error, 'QingdaPwithePDA', 'hello');
};

exports.printPage = function (success, error, data) {
    exec(success, error, 'QingdaPwithePDA', 'printPage', [data]);
};
