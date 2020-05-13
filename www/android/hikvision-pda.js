var exec = require('cordova/exec');

exports.hello = function (success, error) {
    exec(success, error, 'QingdaHikvisionPDA', 'hello');
};

exports.printPage = function (success, error, data) {
    exec(success, error, 'QingdaHikvisionPDA', 'printPage', [data]);
};
