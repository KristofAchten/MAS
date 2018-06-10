arrivalRates = (0.005:0.005:0.05);
arrivalRates = [0.001 arrivalRates(1:end)];

%FCFS
fcfsAverageDelays = [1315524.5 5277452.405063291 8136774.370517928 8544028.786666667 8911129.641975308 8561025.174174175 9653200.301003344 1.0231143361204013*10^7 9153101.53869969 1.000435895*10^7 8619353.214285715 ];    
fcfsUntimelyDeliveries = [2 79 251 300 324 333 299 299 323 300 350]; 

%advanced planning
advancedAverageDelays = [823900.5 4662231.488888889 7699691.521400779 8238354.6866666665 8282780.387205387 7128842.290849674 7575493.402234637 8138257.410179641 8614200.203821655 8613019.228571428 7795497.114551083];    
advancedUntimelyDeliveries = [2 90 257 300 297 306 358 334 314 315 323];

%plots
%delays
figure('Name', 'average delays of untimely delivered users');
hold all;
plot(arrivalRates, fcfsAverageDelays);
plot(arrivalRates, advancedAverageDelays);
xlabel('user arrival rate');
ylabel('average delay of untimely delivered users');
legend('FCFS','advanced planning');

%number of untimely delivered users
figure('Name', 'Number of untimely delivered users');
hold all;
plot(arrivalRates, fcfsUntimelyDeliveries);
plot(arrivalRates, advancedUntimelyDeliveries);
xlabel('user arrival rate');
ylabel('Number of untimely delivered users');
legend('FCFS','advanced planning');